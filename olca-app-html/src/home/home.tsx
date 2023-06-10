import React, { useState, useEffect, CSSProperties } from 'react';
import { render } from 'react-dom';

type Data = {
    version: string;
    lang: string;
    showLibHint: boolean;
};

type Props = {
    data: Data;
};


const NAV_CONFIGURATION = [
//     {
//         navLabel: 'Getting Started',
//         navId: 'getting-started',
//     },
//     {
//         navLabel: "What's new in aicpLCA 1.0",
//         navId: 'whats-new',
//     },
//     {
//         navLabel: 'Collaboration tool for aicpLCA ',
//         navId: 'collaboration-tool',
//     },
//
//     {
//         navLabel: 'Community forum',
//         navId: 'community-forum',
//     },
//     {
//         navLabel: 'Comprehensive database',
//         navId: 'databases',
//     },
//     {
//         navLabel: 'Case studies',
//         navId: 'case-studies',
//     },
//     {
//         navLabel: 'Certified trainings',
//         navId: 'certified-training',
//     },
//     {
//         navLabel: 'Work with aicpLCA experts',
//         navId: 'experts',
//     },
    {
        navLabel: '关于A-LCA工具',
        navId: 'about-a-lca',
    },
    {
        navLabel: '开始使用之前',
        navId: 'before-start',
    },
    {
        navLabel: '获得技术支持',
        navId: 'support',
    }
];

type SupportedLanguages = 'en' | 'de';

const messages: {
    [k in SupportedLanguages]?: {
        [key: string]: string;
    };
} = {
    en: {
        'getting-started.text':
            "<a id=\"openlca\">aicpLCA</a> is a powerful, "
            + "<a id=\"opensource\">open source</a>, feature-rich software for "
            + "LCA and Sustainability modelling. "
            + "\nCreate, import existing databases which contain life cycle "
            + "processes, import assessment methods, create your own "
            + "processes, build your own life cycle models, calculate and "
            + "analyse it. These steps are explained on "
            + "<a id=\"channel\">YouTube</a>, and in the aicpLCA "
            + "<a id=\"manuals\">handbook</a>.",
        'whats-new.text':
            "aicpLCA 2 is a major step forward, with a lot of new features and "
            + "usability improvements. \n"
            + "New features include: new maps and "
            + "better regionalised modelling, broader support for various data "
            + "formats, natural modelling on an LCA canvas, libraries, EPDs "
            + "and results as new elements. For usability, the former model "
            + "graph and Sankey diagram are completely redesigned, many "
            + "editors have been improved, aicpLCA can now run in dark mode "
            + "and the installation on Mac is easier.",
        'community-forum.text':
            "Visit <a id=\"forum\">ask.aicpLCA.org</a> for questions and "
            + "answers around aicpLCA",
        'collaboration-tool.text':
            "The <a id=\"collaboration\">LCA Collaboration Server</a> is "
            + "developed for team work in LCA modelling, dataset "
            + "review and distribution. It is very similar to software code "
            + "development and it is inspired by the world-known Git software. "
            + "It is available for free, on the "
            + "<a id=\"collaboration-download\">aicpLCA website</a>. "
            + "GTech also offers support and "
            + "<a id=\"collaboration-hosting\">hosting services</a>.",
        'databases.text':
            "Find a wide range of free and for-purchase databases for LCA and "
            + "sustainability modelling on "
            + "<a id=\"nexus\">aicpLCA Nexus</a>, which currently boasts "
            + "several hundred thousand datasets. If you have data that you "
            + "would like to share with other users, either for free or for a "
            + "fee, please do not hesitate to contact us. We would be more "
            + "than happy to help you make your valuable contribution "
            + "accessible to a broader audience.",
        'case-studies.text':
            "<a id=\"nexus\">Nexus</a> houses a repository of "
            + "<a id=\"casestudies\">case studies</a> comprising full aicpLCA "
            + "models and accompanying reports for documentation purposes."
            + "\nContact us if you like to share your case study, either for "
            + "free or a fee.",
        'certified-training.text':
            "Trainings on LCA, social LCA, Sustainability and of course "
            + "aicpLCA are available on a regular basis, provided by "
            + "GTech and also by other certified trainers worldwide. They "
            + "are posted and can be booked on "
            + "<a id=\"trainings\">Nexus</a>.",
        'experts.text':
           "aicpLCA is developed by <a id=\"greendelta\">GTech</a> in "
           + "Hong Kong. If you're passionate about making a positive "
           + "impact through your work - whether it's in IT development, data "
           + "development, research, or consultancy - GTech offers "
           + "various open positions. We also "
           + "welcome applications for internships. Check out our current "
           + "opportunities <a id=\"openpositions\">here</a>.",
        'about-a-lca.text':
            "A-LCA是<a id=\"aicp\">汽车行业双碳平台（AICP）</a>提供的专业生命周期评价建模（LCA）工具。A-LCA提供了基于通用生命周期评价框架的建模功能，并帮助用户在一致的方法论和参考数据基础上完成建模。A-LCA建模是产品碳足迹以及多种产品环境声明所必须的基础工作。",
        'before-start.text':
            "在开始使用A-LCA之前，请确保您下载并导入了工具所要求的参考数据库。<br/>"
            + "<a id=\"lci-down\">请点击此处下载</a><br/>",
        'support.text':
            "您可以前往<a id=\"aicphome\">AICP官方网站</a>或者联系AICP平台技术供应商邮箱info@gtech.world询问以获得更多支持。",
    },
};

const getMessage = (
    key: string,
    lang: SupportedLanguages = 'en'
): string | undefined => {
    const langMessages = messages[lang] || messages['en'];
    return langMessages[key];
};

const LibHint = (props: Props) => {
    if (!props || !props.data || !props.data.showLibHint) {
        return <></>;
    }
    const handleClick = () => {
        if (window.onLibHintClick) {
            window.onLibHintClick();
        }
    };
    return (
        <p className="nav-info" onClick={() => handleClick()}>
            <img className="info-icon" src="images/info-32.png"></img>
            Make the calculations in aicpLCA faster. Click here to know more.
        </p>
    );
};

const Navigation = (props: Props) => {
    return (
        <div className="navigation">
            <a
                href="https://gtech.world"
                title="https://gtech.world"
                className="img-link"
            >
                <img
                    className="openlca-logo"
                    src="images/logo_start_page.png"
                />
            </a>
{/*             <div className="nav-info-container">
                <LibHint {...props} />
            </div> */}
        </div>
    );
};

const LeftSection = (props: {
    activeMenu: string;
    setActiveMenu: (value: string) => void;
}) => {
    return (
        <nav className="nav-container">
            <ul className="nav">
                {NAV_CONFIGURATION.map((navItem, index) => (
                    <li
                        key={index}
                        className={`nav-item ${
                            props.activeMenu == navItem.navId ? 'active' : ''
                        }`}
                        onClick={() => props.setActiveMenu(navItem.navId)}
                    >
                        {' '}
                        {navItem.navLabel}
                    </li>
                ))}
            </ul>
        </nav>
    );
};

const RightSection = (props: { activeMenu: string }) => {
    const content = getMessage(`${props.activeMenu}.text`);
    return (
        <div className="right-section-container">
            <div className="content-box">
                <p>
                    <span dangerouslySetInnerHTML={{ __html: content }} />
                </p>
            </div>
        </div>
    );
};

const Footer = () => {
    return (
        <div className="footer-container">
            <a
                className="gd-logo"
                href="https://gtech.world"
                title="https://gtech.world"
            />
        </div>
    );
};

const Page = (props: Props) => {
    const [activeMenu, setActiveMenu] = useState('about-a-lca');

    useEffect(() => {
        bindLinks();
    });

    return (
        <div className="container">
            <div className="max-width-container">
                <header className="header">
                    <Navigation {...props} />
                </header>
                <section className="section">
                    <section className="left-section">
                        <LeftSection
                            activeMenu={activeMenu}
                            setActiveMenu={(value: string) =>
                                setActiveMenu(value)
                            }
                        />
                    </section>
                    <section className="right-section">
                        <RightSection activeMenu={activeMenu} />
                    </section>
                </section>
                <footer className="footer">
                   {/* <Footer />*/}
                </footer>
            </div>
        </div>
    );
};

const bindLinks = () => {
    const config = [
        ['aicp', 'https://aicp.gtech-cn.co'],
        ['aicphome', 'https://aicp.gtech-cn.co'],
        ['lci-down', 'https://github.com/gtech-world/olca-app/releases/latest'],
        ['openlca', 'https://www.openlca.org/'],
        ['news', 'https://www.openlca.org/new'],
        ['opensource', 'https://www.openlca.org/open-source/'],
        ['nexus', 'https://nexus.openlca.org/'],
        ['channel', 'https://www.youtube.com/c/aicpLCA'],
        ['forum', 'https://ask.openlca.org/'],
        ['manuals', 'https://www.openlca.org/learning'],
        ['greendelta', 'https://www.greendelta.com'],
        ['twitter', 'https://twitter.com/aicpLCA'],
        ['blog', 'https://www.openlca.org/blog/'],
        ['trainings', 'https://nexus.openlca.org/service/aicpLCA%20Trainings'],
        ['services', 'https://www.openlca.org/helpdesk'],
        ['collaboration', 'https://www.openlca.org/collaboration-server/'],
        ['collaboration-download', 'https://www.openlca.org/download/'],
        [
            'collaboration-hosting',
            'https://www.openlca.org/lca-collaboration-server-hosting-and-services/'
        ],
        ['casestudies','https://www.openlca.org/case-studies/'],
        [
            'hosting',
            'https://www.openlca.org/lca-collaboration-server-hosting-and-services/',
        ],
        ['openpositions','https://www.greendelta.com/about-us/open-positions']
    ];
    config.forEach(([id, link]) => {
        const elem = document.getElementById(id);
        if (!elem) {
            return;
        }

        if (elem.getAttribute('href')) {
            // avoid adding multiple event handlers
            return;
        }
        elem.setAttribute('href', link);
        elem.setAttribute('title', link);
        elem.addEventListener('click', (e) => {
            if (window.onOpenLink) {
                e.preventDefault();
                window.onOpenLink(link);
            }
        });
    });
};

const setData = (data: Data) => {
    render(<Page data={data} />, document.getElementById('react-root'));
};

setData({
    version: 'Version 1.9.0',
    lang: 'en',
    showLibHint: false,
});

// expose the setData function by binding it to the window object
// onOpenLink can be bound to an event handler (link: string) => void
declare global {
    interface Window {
        setData: any;
        onOpenLink: any;
        onLibHintClick: any;
    }
}
window.setData = setData;
